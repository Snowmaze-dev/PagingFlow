package ru.snowmaze.pagingflow.samples

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.utils.PagingTrigger
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.PREFETCH_DISTANCE
import ru.snowmaze.samples.pagingflow.R

class MainActivity : AppCompatActivity() {

    // TODO сделать пример отображения заглушек, тип возвращаем Flow с айтемами типа Stub, а потом когда подгрузятся элементы возвращаем в эту Flow уже нормальный список
    val viewModel by viewModels<TestViewModel>()
    private val recyclerAdapter by lazy {
        TestAdapter(
            PagingTrigger(
                pagingFlow = { viewModel.pagingFlow },
                prefetchDownDistance = PREFETCH_DISTANCE,
                coroutineScope = lifecycleScope
            ),
            pagingEventsMedium = viewModel.pagingEventsMedium
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<RecyclerView>(R.id.main_recycler_view).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = recyclerAdapter
        }
        findViewById<View>(R.id.button).setOnClickListener {
            lifecycleScope.launch {
                viewModel.pagingFlow.invalidate()
            }
        }
    }
}